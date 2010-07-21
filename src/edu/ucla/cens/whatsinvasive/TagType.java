package edu.ucla.cens.whatsinvasive;

import java.util.Hashtable;
import java.util.Map;

/**
 * The type of the tag.
 * @author Jameel Al-Aziz
 *
 */
public enum TagType {
    WEED(0, "phone/gettags.php"),
    BUG(1, "phone/getanimaltags.php");
    
    private static final Map<Integer,TagType> lookup = new Hashtable<Integer,TagType>();
    private final int value;
    private final String url;
    
    TagType(int value, String url)
    {
        this.value = value;
        this.url = url;
    }
    static {
        for(TagType t : TagType.values())
             lookup.put(t.value(), t);
    }
    
    public static TagType lookup(int value) { return lookup.get(value); }
    public int value() { return value; }
    public String url() { return url; }
}
