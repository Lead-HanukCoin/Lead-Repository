package il.ac.tau.cs.hanukcoin;

public class TestThreads {
    public static void main(String[] args) {
        String threadName;
        Runnable r = new Runnable() {
            @Override
            public void run() {

            }
        };
        Thread t = new Thread(r);
        t.start();
    }
}

