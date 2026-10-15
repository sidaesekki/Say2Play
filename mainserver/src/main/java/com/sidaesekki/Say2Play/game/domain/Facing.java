package com.sidaesekki.Say2Play.game.domain;

// 시계 방향 순서. 좌표는 오른쪽이 x+, 아래쪽이 y+다.
public enum Facing {
    N(0, -1), E(1, 0), S(0, 1), W(-1, 0);

    private final int dx;
    private final int dy;

    Facing(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public Facing turnRight() {
        return values()[(ordinal() + 1) % 4];
    }

    public Facing turnLeft() {
        return values()[(ordinal() + 3) % 4];
    }
}
