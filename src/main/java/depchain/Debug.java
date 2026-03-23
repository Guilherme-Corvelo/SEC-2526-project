package depchain;

public class Debug {
    final static boolean debug=true;

    public static void debug(String str){
        if(debug){
            System.out.println("[DEBUG]" + str + "\n");
        }
    }
}
