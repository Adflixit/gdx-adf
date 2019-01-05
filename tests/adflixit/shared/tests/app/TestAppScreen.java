/*
 * Copyright 2018 Adflixit
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package adflixit.shared.tests.app;

import static adflixit.shared.BaseGame.internalFile;

import adflixit.shared.BaseScreen;

public class TestAppScreen extends BaseScreen<TestApp> {
  private class Screen extends TestAppScreen {
    public Screen(TestApp game) {
      super(game);
    }

    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
      super.touchDown(screenX, screenY, pointer, button);
      glog(touch.x+" "+touch.y);
      return true;
    }
  }

  public TestAppScreen(TestApp game) {
    super(game);
  }

  @Override public void goBackAction() {
  }
}
