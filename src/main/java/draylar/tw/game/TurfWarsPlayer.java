package draylar.tw.game;

import draylar.tw.game.team.Team;

public class TurfWarsPlayer {

    private Team team;
    private int arrowCooldown = 0;

    public TurfWarsPlayer(Team team) {
        this.team = team;
    }

    public int getArrowCooldown() {
        return arrowCooldown;
    }

    public void incrementArrowCooldown() {
        arrowCooldown++;
    }

    public void resetArrowCooldown() {
        arrowCooldown = 0;
    }

    public Team getTeam() {
        return team;
    }
}
