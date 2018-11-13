package PenguinT;

public class Utility
{
    public static String Join(String[] tokens, String infix)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i != tokens.length - 1; i++)
        {
            sb.append(tokens[i]);
            if (infix != null) sb.append(infix);
        }
        sb.append(tokens[tokens.length - 1]);

        return sb.toString();
    }
}
