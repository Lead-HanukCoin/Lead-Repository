package il.ac.tau.cs.hanukcoin;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Random;

public class HanukCoinUtils {
    static private final int PUZZLE_BITS0 = 20;  // Note - we can make it lower for quick testing
    /**
     * Calculate how many time n can be divided by 2 to get zero
     * @param n
     * @return base 2 log of n plus 1
     */
    public static int numBits(long n) {
        for(int i =0 ; i < 32; i++) {
            long mask = (1L << i) - 1;
            if ((n & mask) == n) {
                return i;
            }
        }
        return 99; //error?
    }

    /**
     * Given a block serial number - how many zeros should be at the end of its signature
     * @param blockSerialNumber
     * @return number of required zero at end
     */
    public static int numberOfZerosForPuzzle(int blockSerialNumber) {
        return PUZZLE_BITS0 + numBits(blockSerialNumber);
    }

    /**
     * Read 4 bytes big endian integer from data[offset]
     * @param data  - block of bytes
     * @param offset - where to start reading 4 bytes
     * @return 4 bytes integer
     */
    public static int intFromBytes(byte[] data, int offset) {
        //return data[offset] << 24 | data[offset + 1] << 16 | data[offset + 2] << 8 | data[offset + 3];
        int b1 = (data[offset] & 0xFF) << 24;
        int b2 = (data[offset + 1] & 0xFF) << 16;
        int b3 = (data[offset + 2] & 0xFF) << 8;
        int b4 = (data[offset + 3] & 0xFF);
        return b1 | b2 | b3 | b4;
    }

    /**
     * put value in big-endian format into data[offser]
     * @param data - bytes array
     * @param offset - offeset into data[] where to write value
     * @param value - 32 bit value
     */
    public static void intIntoBytes(byte[] data, int offset, int value) {
        //return data[offset] << 24 | data[offset + 1] << 16 | data[offset + 2] << 8 | data[offset + 3];
        data[offset] = (byte)((value >> 24) & 0xFF);
        data[offset + 1] = (byte)((value >> 16) & 0xFF);
        data[offset + 2] = (byte)((value >> 8) & 0xFF);
        data[offset + 3] = (byte)((value) & 0xFF);
    }

    /**
     * Given a user/team name - return 32bit wallet code
     * @return 32bit number
     */
    public static int walletCode(String teamName) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");  // may cause NoSuchAlgorithmException
            byte[] messageDigest = md.digest(teamName.getBytes());
            return intFromBytes(messageDigest, 0);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Internal error - missing MD5");
        }
    }

    static private byte[] parseByteStr(String s) {
        ArrayList<Byte> a = new ArrayList<Byte>();
        for (String hex : s.split("\\s+")) {
            byte b = (byte) Integer.parseInt(hex, 16);
            a.add(b);
        }
        byte[] result = new byte[a.size()];
        for(int i = 0; i < a.size(); i++) {
            result[i] = a.get(i);
        }
        return result;
    }

    public static Block createBlock0forTestStage() {
        byte[] puzzle =  parseByteStr("71 16 8F 29  D9 FE DF F9");
        byte[] sig = parseByteStr("BF 3D AE 1F  65 B0 8F 66 AB 2D B5 1E");
        return Block.create(0, 0, "TEST_BLK".getBytes(), puzzle, sig);
    }

    /**
     * Check that the last nZeros bits of sig[16] are all zeros
     * @param sig - MD5 16 bytes signature
     * @param nZeros - number of required zeros at the end
     * @return true if last bits are zeros
     */
    public static boolean checkSignatureZeros(byte[] sig, int nZeros) {
        if (sig.length != 16) {
            return false; // bad signature
        }
        int sigIndex = 15;  // start from last byte of MD5
        // First check in chunks of 8 bits - full bytes
        while(nZeros >= 8) {
            if (sig[sigIndex] != 0)
                return false;
            sigIndex -= 1;
            nZeros -= 8;
        }
        if (nZeros == 0) {
            return true;
        }
        //We have several bits to check for zero
        int mask = (1 << nZeros) - 1;  // mask for the last bits
        return (sig[sigIndex] & mask) == 0;
    }

    public static int getUnixTimestamp() {
        return (int)(System.currentTimeMillis() / 1000);
    }

    /**
     *
     * @param len - number of bytes to compare
     * @param a - first array
     * @param a_start - start offset for a
     * @param b - second array
     * @param b_start - start offset for b
     * @return true if array parts are equal
     */
    public static boolean ArraysPartEquals(int len, byte[] a, int a_start, byte[] b, int b_start) {
        for(int i = 0; i < len; i++) {
            if (a[a_start + i] != b[b_start + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Do several attempts at zolving the puzzle
     * @param myWalletNum - wallet number to mine for
     * @param prevBlock - the previos block in the chain
     * @param attemptsCount - number of attemts
     * @return a new block OR null if failed
     */
    public static Block mineCoinAtteempt(int myWalletNum, Block prevBlock, int attemptsCount) {
        int newSerialNum = prevBlock.getSerialNumber() + 1;
        byte[] prevSig = new byte[8];
        System.arraycopy(prevBlock.getBytes(), 24, prevSig, 0, 8);
        Block newBlock = Block.createNoSig(newSerialNum, myWalletNum, prevSig);
        Random rand = new Random();
        for (int attempt= 0; attempt < attemptsCount; attempt++) {
            long puzzle = rand.nextLong();
            newBlock.setLongPuzzle(puzzle);
            Block.BlockError result = newBlock.checkSignature();
            if (result != Block.BlockError.SIG_NO_ZEROS) {
                // if enough zeros - we got error because of other reason - e.g. sig field not set yet
                byte[] sig = newBlock.calcSignature();
                newBlock.setSignaturePart(sig);
                // recheck block
                result = newBlock.checkSignature();
                if (result != Block.BlockError.OK) {
                    return null; //failed
                }
                return newBlock;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        int numCoins = Integer.parseInt(args[0]);
        System.out.println(String.format("Mining %d coins...", numCoins));
        ArrayList<Block> chain = new ArrayList<>();
        Block genesis = HanukCoinUtils.createBlock0forTestStage();
        chain.add(genesis);
        int wallet1 = HanukCoinUtils.walletCode("TEST1");
        int wallet2 = HanukCoinUtils.walletCode("TEST2");

        for(int i = 0; i < numCoins; i++) {
            long t1 = System.nanoTime();
            Block newBlock = null;
            Block prevBlock = chain.get(i);
            while (newBlock == null) {
                newBlock = mineCoinAtteempt(wallet1, prevBlock, 10000000);
            }
            int tmp = wallet1;
            wallet1 = wallet2;
            wallet2 = tmp;
            if (newBlock.checkValidNext(prevBlock) != Block.BlockError.OK) {
                throw new RuntimeException("BAD BLOCK");
            }
            chain.add(newBlock);
            long t2 = System.nanoTime();
            System.out.println(String.format("mining took =%d milli", (int)((t2 - t1)/10000000)));
            System.out.println(newBlock.binDump());

        }


    }
}
