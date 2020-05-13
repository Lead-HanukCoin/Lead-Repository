package il.ac.tau.cs.hanukcoin;


import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import static il.ac.tau.cs.hanukcoin.HanukCoinUtils.mineCoinAtteempt;


public class HanukCoinUtilsTest extends TestCase{
    @org.junit.Test
    public void test_numBits() throws Exception {
        assertEquals(HanukCoinUtils.numBits(0), 0);
        assertEquals(HanukCoinUtils.numBits(32), 6);
        assertEquals(HanukCoinUtils.numBits(31), 5);
        assertEquals(HanukCoinUtils.numBits(5), 3);
    }

    @org.junit.Test
    public void test_intFromIntoBytes() {
        byte[] data = new byte[9];
        int x = 0xDeadBeef;
        HanukCoinUtils.intIntoBytes(data, 4, x);
        assertEquals(HanukCoinUtils.intFromBytes(data, 4), x);
        x = -1411231107;
        HanukCoinUtils.intIntoBytes(data, 0, x);
        assertEquals(HanukCoinUtils.intFromBytes(data, 0), x);
        // This is the "Java way" to do intFromBytes() - I chose not to use it.
        // DataInputStream uses big endien. Checking this is indeed the same.
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        try {
            assertEquals(dis.readInt(), x);
        } catch (IOException e) {
            assert(false);
        }
        // Checking timing - checking if indeed the coice to use the "less Java way" was correct
        long t1 = System.nanoTime();
        int i;
        final int passes = 100000;
        for (i = 0; i < passes; i++) {
            assertEquals(HanukCoinUtils.intFromBytes(data, 0), x);
        }
        long t2 = System.nanoTime();
        try {
            for (i = 0; i < passes; i++) {
                dis = new DataInputStream(new ByteArrayInputStream(data));
                assertEquals(dis.readInt(), x);
            }
        } catch (IOException e) {
            assert(false);
        }
        long t3 = System.nanoTime();
        long delta1 = t2 - t1;
        long delta2 = t3 - t2;
        System.out.println(String.format("time intFromIntoBytes=%d time DataInputStream=%d", delta1, delta2));
        assert(delta2 > 2 * delta1);
    }

    @org.junit.Test
    public void test_mine() {
        Block genesis = HanukCoinUtils.createBlock0forTestStage();
        long t1 = System.nanoTime();
        Block newBlock = mineCoinAtteempt(HanukCoinUtils.walletCode("TEST"), genesis, 10000000);
        long t2 = System.nanoTime();
        System.out.println(String.format("mining took =%d milli", (int)((t2 - t1)/10000000)));

        System.out.println(newBlock.binDump());
    }


        @org.junit.Test
    public void test_numberOfZerosForPuzzle() {
        assertEquals(HanukCoinUtils.numberOfZerosForPuzzle(1000), 30);
    }

    @org.junit.Test
    public void test_walletCode() {
        assertEquals(HanukCoinUtils.walletCode("Foo Bar,Bar Vaz"), -1411231107);
    }

}