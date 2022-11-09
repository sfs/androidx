/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.compiler.plugins.kotlin.facade

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import java.nio.file.Path
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.jvm.JvmGeneratorExtensions
import org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory
import org.jetbrains.kotlin.backend.jvm.JvmIrDeserializerImpl
import org.jetbrains.kotlin.backend.jvm.jvmPhases
import org.jetbrains.kotlin.backend.jvm.serialization.JvmIdSignatureDescriptor
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.NoScopeRecordCliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.PsiBasedProjectFileSearchScope
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.compiler.VfsBasedProjectEnvironment
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.diagnostics.DiagnosticReporterFactory
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.collectors.FirDiagnosticsCollector
import org.jetbrains.kotlin.fir.backend.Fir2IrComponents
import org.jetbrains.kotlin.fir.backend.Fir2IrConverter
import org.jetbrains.kotlin.fir.backend.jvm.Fir2IrJvmSpecialAnnotationSymbolProvider
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmBackendClassResolver
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmBackendExtension
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmKotlinMangler
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmVisibilityConverter
import org.jetbrains.kotlin.fir.backend.jvm.JvmFir2IrExtensions
import org.jetbrains.kotlin.fir.builder.PsiHandlingMode
import org.jetbrains.kotlin.fir.builder.RawFirBuilder
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.resolve.transformers.FirTotalResolveProcessor
import org.jetbrains.kotlin.fir.session.FirSessionFactoryHelper
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmDescriptorMangler
import org.jetbrains.kotlin.ir.backend.jvm.serialization.JvmIrMangler
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices

class FirAnalysisResult(
    val session: FirSession,
    val scopeSession: ScopeSession,
    val firFiles: List<FirFile>,
    override val files: List<KtFile>
): AnalysisResult {
    override val diagnostics: List<AnalysisResult.Diagnostic>
        get() {
            val collector = FirDiagnosticsCollector.create(session, scopeSession)
            return firFiles.flatMap { file ->
                val reporter = DiagnosticReporterFactory.createReporter()
                collector.collectDiagnostics(file, reporter)
                reporter.diagnostics.map {
                    AnalysisResult.Diagnostic(it.factoryName, it.textRanges)
                }
            }
        }

    override val bindingContext: BindingContext?
        get() = null
}

class FirFrontendResult(
    val irModule: IrModuleFragment,
    val components: Fir2IrComponents,
    val generatorExtensions: JvmGeneratorExtensions
)

class K2CompilerFacade(environment: KotlinCoreEnvironment) : KotlinCompilerFacade(environment) {
    init {
        PsiElementFinder.EP.getPoint(environment.project)
            .unregisterExtension(JavaElementFinder::class.java)
    }

    private val project: Project
        get() = environment.project

    private val configuration: CompilerConfiguration
        get() = environment.configuration

    override fun analyze(files: List<SourceFile>): FirAnalysisResult {
        val ktFiles = files.map { it.toKtFile(project) }
        val scope = GlobalSearchScope.filesScope(project, ktFiles.map { it.virtualFile })
            .uniteWith(TopDownAnalyzerFacadeForJVM.AllJavaSourcesInProjectScope(project))
        val librariesScope = ProjectScope.getLibrariesScope(project)

        val session = createSessionForTests(
            scope,
            librariesScope,
            "main",
            getPackagePartProvider = environment::createPackagePartProvider
        )

        val firProvider = session.firProvider as FirProviderImpl
        val builder = RawFirBuilder(session, firProvider.kotlinScopeProvider, PsiHandlingMode.COMPILER)
        val firFiles = ktFiles.map {
            builder.buildFirFile(it).also { firFile ->
                firProvider.recordFile(firFile)
            }
        }

        val scopeSession = FirTotalResolveProcessor(session).also {
            it.process(firFiles)
        }.scopeSession

        return FirAnalysisResult(session, scopeSession, firFiles, ktFiles)
    }

    private fun frontend(files: List<SourceFile>): FirFrontendResult {
        val analysisResult = analyze(files)
        val fir2IrExtensions = JvmFir2IrExtensions(configuration, JvmIrDeserializerImpl(), JvmIrMangler)

        val commonFirFiles = analysisResult.session.moduleData.dependsOnDependencies
            .map { it.session }
            .filter { it.kind == FirSession.Kind.Source }
            .flatMap { (it.firProvider as FirProviderImpl).getAllFirFiles() }

        val result = Fir2IrConverter.createModuleFragmentWithSignaturesIfNeeded(
            analysisResult.session,
            analysisResult.scopeSession,
            analysisResult.firFiles + commonFirFiles,
            configuration.languageVersionSettings,
            JvmIdSignatureDescriptor(JvmDescriptorMangler(null)),
            fir2IrExtensions,
            FirJvmKotlinMangler(analysisResult.session),
            JvmIrMangler,
            IrFactoryImpl,
            FirJvmVisibilityConverter,
            Fir2IrJvmSpecialAnnotationSymbolProvider(),
            IrGenerationExtension.getInstances(project),
            generateSignatures = false
        )

        return FirFrontendResult(result.irModuleFragment, result.components, fir2IrExtensions)
    }

    override fun compileToIr(files: List<SourceFile>): IrModuleFragment =
        frontend(files).irModule

    override fun compile(files: List<SourceFile>): GenerationState {
        val frontendResult = frontend(files)
        val irModuleFragment = frontendResult.irModule
        val components = frontendResult.components

        val generationState = GenerationState.Builder(
            project,
            ClassBuilderFactories.TEST,
            irModuleFragment.descriptor,
            NoScopeRecordCliBindingTrace().bindingContext,
            configuration
        ).isIrBackend(
            true
        ).jvmBackendClassResolver(
            FirJvmBackendClassResolver(components)
        ).build()

        generationState.beforeCompile()
        val codegenFactory = JvmIrCodegenFactory(
            configuration,
            configuration.get(CLIConfigurationKeys.PHASE_CONFIG) ?: PhaseConfig(jvmPhases)
        )
        codegenFactory.generateModuleInFrontendIRMode(
            generationState, irModuleFragment, components.symbolTable, components.irProviders,
            frontendResult.generatorExtensions, FirJvmBackendExtension(components.session, components),
        ) {}
        generationState.factory.done()
        return generationState
    }

    private fun createSessionForTests(
        sourceScope: GlobalSearchScope,
        librariesScope: GlobalSearchScope,
        moduleName: String = "TestModule",
        friendsPaths: List<Path> = emptyList(),
        getPackagePartProvider: (GlobalSearchScope) -> PackagePartProvider,
    ): FirSession {
        return FirSessionFactoryHelper.createSessionWithDependencies(
            Name.identifier(moduleName),
            JvmPlatforms.unspecifiedJvmPlatform,
            JvmPlatformAnalyzerServices,
            externalSessionProvider = null,
            VfsBasedProjectEnvironment(
                project,
                VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL),
                getPackagePartProvider
            ),
            languageVersionSettings = LanguageVersionSettingsImpl.DEFAULT,
            PsiBasedProjectFileSearchScope(sourceScope),
            PsiBasedProjectFileSearchScope(librariesScope),
            lookupTracker = null,
            enumWhenTracker = null,
            incrementalCompilationContext = null,
            extensionRegistrars = FirExtensionRegistrar.getInstances(project),
            needRegisterJavaElementFinder = true,
            dependenciesConfigurator = {
                friendDependencies(friendsPaths)
            }
        )
    }
}
