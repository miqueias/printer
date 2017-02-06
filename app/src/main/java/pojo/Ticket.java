package pojo;

import java.util.ArrayList;

/**
 * Created by Miqueias on 2/6/17.
 */

public class Ticket {

    public int id;
    public String gambler_name;
    public String ticket_value;
    public int validated_user;
    public String validated_date;
    public String validated_commission;
    public String created_at;
    public String updated_at;
    public String deleted_at;
    public Validator validator;
    public ArrayList<Betting> betting;


    public Ticket() {

    }


}
