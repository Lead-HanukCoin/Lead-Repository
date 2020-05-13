package il.ac.tau.cs.hanukcoin;

import javafx.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class ConnectionsList {
    public static ArrayList<ShowChain.NodeInfo> activeNodes = new ArrayList<>();
    public static HashMap<Pair<String, Integer>, ShowChain.NodeInfo> hmap = new HashMap<Pair<String, Integer>, ShowChain.NodeInfo>();

    public static void add(ShowChain.NodeInfo node){
        hmap.put(new Pair(node.host, node.port), node);
    }

    public static Iterator<ShowChain.NodeInfo> getValuesIterator(){
        return hmap.values().iterator();
    }
}
