package il.ac.tau.cs.hanukcoin;

import java.util.ArrayList;
import java.util.Iterator;

public class BlocksList {
    public static ArrayList<Block> blist = new ArrayList<>();

    public Iterator<Block> getBlocksIterator(){
        return blist.iterator();
    }
    public boolean checkValid(){ // checks if the whole list is valid
        Block current = null;
        Block next;
        for (Iterator<Block> it = getBlocksIterator(); it.hasNext(); ) {
            next = it.next();
            if(current == null) {
                current = next;
                continue;
            }
            if(next.checkValidNext(current) != Block.BlockError.OK){
                return false;
            }
            current = next;
        }
        return true;
    }
}
