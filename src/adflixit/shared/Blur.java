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

package adflixit.shared;

import static adflixit.shared.TweenUtils.*;
import static aurelienribon.tweenengine.TweenCallback.*;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenCallback;
import aurelienribon.tweenengine.equations.Quart;
import aurelienribon.tweenengine.primitives.MutableFloat;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * Performs two-pass Gaussian blur.
 */
@Deprecated public class Blur extends ScreenComponent<BaseScreen<?>> {
  private final String		uniName			= "u_blur";
  private ShaderProgram		firstPass;
  private ShaderProgram		lastPass;
  private FrameBuffer		firstFrameBuffer;
  private FrameBuffer		lastFrameBuffer;
  private int			passes			= 1;	// number of blurring cycles
  private final MutableFloat	amount			= new MutableFloat(0);
  /** Pass is used to access the route to update the shader info.
   * Schedule is used to update the info one time after which it resets. */
  private boolean		pass, scheduled;

  public Blur(BaseScreen<?> screen) {
    super(screen);
  }

  public Blur(BaseScreen<?> screen, int passes) {
    super(screen);
    setPasses(passes);
  }

  public Blur(BaseScreen<?> screen, FileHandle hvert, FileHandle hfrag, FileHandle vvert, FileHandle vfrag) {
    super(screen);
    load(hvert, hfrag, vvert, vfrag);
  }

  public Blur(BaseScreen<?> screen, String hvert, String hfrag, String vvert, String vfrag) {
    super(screen);
    load(hvert, hfrag, vvert, vfrag);
  }

  public void load(FileHandle hvert, FileHandle hfrag, FileHandle vvert, FileHandle vfrag) {
    firstPass = new ShaderProgram(hvert, hfrag);
    lastPass = new ShaderProgram(vvert, vfrag);
  }

  public void load(String hvert, String hfrag, String vvert, String vfrag) {
    firstPass = new ShaderProgram(hvert, hfrag);
    lastPass = new ShaderProgram(vvert, vfrag);
  }

  public Blur setPasses(int i) {
    passes = i;
    return this;
  }

  public void reset() {
    resetAmount();
    lock();
    unschedule();
  }

  public void draw() {
    Texture tex;
    float x = scr.cameraX0(), y = scr.cameraY0();
    for (int i=0; i < passes; i++) {
      pass(i, x, y);
    }
    bat.setShader(null);
    bat.begin();
      tex = lastFrameBuffer.getColorBufferTexture();
      bat.draw(tex, x, y, scr.screenWidth(), scr.screenHeight(), 0,0,1,1);
    bat.end();
    if (scheduled) {
      unschedule();
    }
  }

  public Texture inputTex() {
    return scr.fbTex();
  }

  /** Performs the blurring routine.
   * @param i iterations
   * @param x result drawing x
   * @param y result drawing y */
  private void pass(int i, float x, float y) {
    Texture tex;
    // horizontal pass
    bat.setShader(firstPass);
    firstFrameBuffer.begin();
    bat.begin();
      if (pass || scheduled) {
        firstPass.setUniformf(uniName, amount());
      }
      tex = i > 0 ? lastFrameBuffer.getColorBufferTexture() : inputTex();
      bat.draw(tex, x, y, scr.screenWidth(), scr.screenHeight());
    bat.end();
    firstFrameBuffer.end();
    // vertical pass
    bat.setShader(lastPass);
    lastFrameBuffer.begin();
    bat.begin();
      if (pass || scheduled) {
        lastPass.setUniformf(uniName, amount());
      }
      tex = firstFrameBuffer.getColorBufferTexture();
      bat.draw(tex, x, y, scr.screenWidth(), scr.screenHeight());
    bat.end();
    lastFrameBuffer.end();
  }

  /** Locks the shader update route. */
  private void lock() {
    pass = false;
  }

  /** Unlocks the shader update route. */
  private void unlock() {
    pass = true;
  }

  /** Schedules a one-time access to the update route. */
  private void schedule() {
    scheduled = true;
  }

  /** Resets the one-time access to the update route. */
  private void unschedule() {
    scheduled = false;
  }

  public boolean isActive() {
    return amount() > 0;	
  }

  public float amount() {
    return amount.floatValue();
  }

  public void setAmount(float v) {
    killTweenTarget(amount);
    amount.setValue(v);
    schedule();
  }

  public void resetAmount() {
    setAmount(0);
  }

  /** @param v value
   * @param d duration */
  public Tween $tween(float v, float d) {
    killTweenTarget(amount);
    return Tween.to(amount, 0, d).target(v).ease(Quart.OUT)
           .setCallback((type, source) -> {
             if (type==BEGIN) {
               unlock();
             } else {
               lock();
             }
           })
           .setCallbackTriggers(BEGIN|COMPLETE);
  }

  /** @param v value
   * @param d duration */
  public Tween $tweenOut(float d) {
    return $tween(0, d);
  }

  /** @param v value */
  public Tween $setAmount(float v) {
    killTweenTarget(amount);
    return Tween.set(amount, 0).target(v).setCallback((type, source) -> schedule());
  }

  public Tween $resetAmount() {
    return $setAmount(0);
  }

  public void dispose() {
    firstPass.dispose();
    lastPass.dispose();
    firstFrameBuffer.dispose();
    lastFrameBuffer.dispose();
  }

  public void resize() {
    if (firstResize) {
      firstResize = false;
    } else {
      firstFrameBuffer.dispose();
      lastFrameBuffer.dispose();
    }
    firstFrameBuffer = new FrameBuffer(Format.RGB888, scr.frameBufferWidth(), scr.frameBufferHeight(), false);
    lastFrameBuffer = new FrameBuffer(Format.RGB888, scr.frameBufferWidth(), scr.frameBufferHeight(), false);
  }
}
