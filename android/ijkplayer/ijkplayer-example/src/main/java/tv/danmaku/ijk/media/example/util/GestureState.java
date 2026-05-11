/*
 * Copyright (C) 2015 Bilibili
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

package tv.danmaku.ijk.media.example.util;

/**
 * Lightweight state container for touch gesture tracking in the player.
 *
 * Covers three gesture families:
 *   1. Edge-back swipe (system back gesture from screen edges)
 *   2. Vertical swipe — left half = brightness, right half = volume
 *   3. Horizontal swipe — seek forward / backward
 *   4. Double-tap — toggle pause / resume
 *
 * All state fields are package-accessible for performance; they are only
 * read/written by VideoActivity's dispatchTouchEvent code path.
 */
public class GestureState {

    // --- Edge-back swipe ---
    public boolean edgeBackActive = false;
    public boolean edgeBackFromLeft = false;
    public float edgeBackDownX = 0f;
    public float edgeBackDownY = 0f;

    // --- Vertical brightness / volume gesture ---
    public boolean verticalActive = false;
    public boolean brightnessSide = false;   // true=left(brightness), false=right(volume)
    public float gestureDownX = 0f;
    public float gestureDownY = 0f;
    public float startBrightness = 0f;
    public int startVolume = 0;
    public int maxVolume = 0;

    // --- Horizontal seek gesture ---
    public boolean seekActive = false;
    public long seekStartMs = 0L;
    public long seekTargetMs = 0L;

    // --- Double-tap ---
    public long lastTapTime = 0L;
    public float lastTapX = -1f;
    public float lastTapY = -1f;

    /** Reset all in-progress gesture flags (called on ACTION_DOWN). */
    public void reset() {
        verticalActive = false;
        seekActive = false;
        // Note: edgeBackActive is reset inside the edge-back check block, not here.
    }

    /** Reset state after gesture is fully committed or cancelled. */
    public void onUp() {
        edgeBackActive = false;
        verticalActive = false;
        seekActive = false;
    }
}
