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

package androidx.compose.compiler.plugins.kotlin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isSuspend
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.FirAnonymousFunctionTransformerExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.resolve.FirSamConversionTransformerExtension
import org.jetbrains.kotlin.fir.resolve.constructFunctionalType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.CustomAnnotationTypeAttribute
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.withAttributes

class ComposeFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::ComposeFirSamConversionTransformer
        +::ComposeFirAnonymousFunctionTransformer
    }
}

class ComposeFirSamConversionTransformer(
    session: FirSession
) : FirSamConversionTransformerExtension(session) {
    override fun getCustomFunctionalTypeForSamConversion(
        function: FirSimpleFunction
    ): ConeLookupTagBasedType? =
        function.constructComposableFunctionalType()
}

class ComposeFirAnonymousFunctionTransformer(
    session: FirSession
) : FirAnonymousFunctionTransformerExtension(session) {
    override fun getCustomFunctionalTypeForAnonymousFunction(
        function: FirAnonymousFunction,
        isSuspend: Boolean
    ): ConeKotlinType? =
        function.constructComposableFunctionalType(isSuspend)

    override fun getInferredAnnotationsForAnonymousFunction(
        anonymousFunction: FirAnonymousFunction,
        expectedType: ConeKotlinType
    ): List<FirAnnotation> =
        expectedType.customAnnotations.getAnnotationByClassId(ComposeClassIds.Composable)
            ?.takeIf { !anonymousFunction.hasAnnotation(ComposeClassIds.Composable) }
            ?.let { listOf(it) } ?: listOf()
}

private fun FirFunction.constructComposableFunctionalType(
    isSuspend: Boolean = this.isSuspend
): ConeLookupTagBasedType? =
    getAnnotationByClassId(ComposeClassIds.Composable)?.let {
        constructFunctionalType(isSuspend).withAnnotation(it)
    }

private fun <T : ConeKotlinType> T.withAnnotation(annotation: FirAnnotation): T =
    withAttributes(attributes + CustomAnnotationTypeAttribute(listOf(annotation)))
