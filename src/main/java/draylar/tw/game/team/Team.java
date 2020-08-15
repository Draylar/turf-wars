package draylar.tw.game.team;

public enum Team {
    RED(16733525),
    BLUE(5592575);

    private final int color;

    Team(int color) {
        this.color = color;
    }

    public int getColor() {
        return color;
    }
}
