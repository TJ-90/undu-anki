package com.tj90.unduanki.game.runner

/** Maps one single-pointer gesture in density-independent pixels to at most one action. */
object RunnerInput {
    fun swipe(dxDp: Float, dyDp: Float): RunnerAction? {
        if (!dxDp.isFinite() || !dyDp.isFinite()) return null
        if (kotlin.math.max(kotlin.math.abs(dxDp), kotlin.math.abs(dyDp)) < 24f) return null
        return if (kotlin.math.abs(dxDp) > kotlin.math.abs(dyDp)) {
            if (dxDp < 0) RunnerAction.LEFT else RunnerAction.RIGHT
        } else {
            if (dyDp < 0) RunnerAction.JUMP else RunnerAction.SLIDE
        }
    }
}
