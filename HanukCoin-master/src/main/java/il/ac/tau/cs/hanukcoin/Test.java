package il.ac.tau.cs.hanukcoin;

import java.io.*;

public class Test {
    public static void main(String[] args) {
        File test = new File("test.txt");
        try {
            DataOutputStream fileDataOut = new DataOutputStream(new FileOutputStream(test));
            DataInputStream fileDataIn = new DataInputStream(new FileInputStream(test));
            try {
                fileDataOut.writeInt(5);
                int n = fileDataIn.readInt();
                System.out.println(n);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
