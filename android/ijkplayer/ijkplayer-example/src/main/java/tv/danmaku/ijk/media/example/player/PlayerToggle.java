package tv.danmaku.ijk.media.example.player;

import tv.danmaku.ijk.media.example.application.Settings;

public final class PlayerToggle {
    private PlayerToggle() {
    }

    public static ToggleResult toggleCore(int currentPlayer) {
        if (currentPlayer == Settings.PV_PLAYER__IjkExoMediaPlayer) {
            return new ToggleResult(Settings.PV_PLAYER__IjkMediaPlayer, false);
        }
        return new ToggleResult(Settings.PV_PLAYER__IjkExoMediaPlayer, true);
    }

    public static final class ToggleResult {
        public final int nextPlayer;
        public final boolean preferExoForHttp;

        public ToggleResult(int nextPlayer, boolean preferExoForHttp) {
            this.nextPlayer = nextPlayer;
            this.preferExoForHttp = preferExoForHttp;
        }
    }
}

