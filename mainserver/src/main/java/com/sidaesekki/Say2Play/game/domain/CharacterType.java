package com.sidaesekki.Say2Play.game.domain;

// 시작 상태는 erd.md "CHARACTER_START"를 따른다.
public enum CharacterType {
    PLAYER(6, 7, Facing.N),
    ENEMY(6, 1, Facing.S);

    private final int startX;
    private final int startY;
    private final Facing startFacing;

    CharacterType(int startX, int startY, Facing startFacing) {
        this.startX = startX;
        this.startY = startY;
        this.startFacing = startFacing;
    }

    public int startX() {
        return startX;
    }

    public int startY() {
        return startY;
    }

    public Facing startFacing() {
        return startFacing;
    }
}
