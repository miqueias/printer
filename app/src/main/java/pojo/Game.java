package pojo;

/**
 * Created by Miqueias on 2/6/17.
 */

public class Game {

    private int id;
    private int user_id;
    private String game_date;
    private String game_time;
    private String home_team;
    private String out_team;
    private String championship;
    private String home_goals;
    private String out_goals;
    private String house;

    public Game() {

    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUser_id() {
        return user_id;
    }

    public void setUser_id(int user_id) {
        this.user_id = user_id;
    }

    public String getGame_date() {
        return game_date;
    }

    public void setGame_date(String game_date) {
        this.game_date = game_date;
    }

    public String getGame_time() {
        return game_time;
    }

    public void setGame_time(String game_time) {
        this.game_time = game_time;
    }

    public String getHome_team() {
        return home_team;
    }

    public void setHome_team(String home_team) {
        this.home_team = home_team;
    }

    public String getOut_team() {
        return out_team;
    }

    public void setOut_team(String out_team) {
        this.out_team = out_team;
    }

    public String getChampionship() {
        return championship;
    }

    public void setChampionship(String championship) {
        this.championship = championship;
    }

    public String getHome_goals() {
        return home_goals;
    }

    public void setHome_goals(String home_goals) {
        this.home_goals = home_goals;
    }

    public String getOut_goals() {
        return out_goals;
    }

    public void setOut_goals(String out_goals) {
        this.out_goals = out_goals;
    }

    public String getHouse() {
        return house;
    }

    public void setHouse(String house) {
        this.house = house;
    }
}
