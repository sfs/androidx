/*
 * Copyright (C) 2014 The Android Open Source Project
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

package androidx.test.uiautomator.testapp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class UiObject2TestClickActivity extends Activity {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.uiobject2_testclick_activity);

        // Set up the long-clickable buttons.
        Button button4 = (Button) findViewById(R.id.button4);
        Button button5 = (Button) findViewById(R.id.button5);
        Button button6 = (Button) findViewById(R.id.button6);
        Button button7 = (Button) findViewById(R.id.button7);

        button4.setOnLongClickListener(new OnButtonLongClick());
        button5.setOnLongClickListener(new OnButtonLongClick());
        button6.setOnLongClickListener(new OnButtonLongClick());
        button7.setOnLongClickListener(new OnButtonLongClick());
    }

    public void onButtonClick(@NonNull View v) {
        ((Button) v).append("_clicked");
    }

    static class OnButtonLongClick implements OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            ((Button) v).append("_long_clicked");
            return true;
        }
    }
}
