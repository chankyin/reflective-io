# Reflective-IO
Version-sensitive binary data saving/transfer library based on Java reflections

## Format documentation
```
Legend:
  BASICS:
    BOOLEAN:    x00 (FALSE) or x01 (TRUE)
    VINT_x:     integer with x bytes long (reversed if small-endian)
    BYTE:       eq. VINT_1
    SHORT:      eq. VINT_2
    INT:        eq. VINT_4
    LONG:       eq. VINT_8
    FLOAT:      binary output from Float.floatToIntBits
    DOUBLE:     binary output from Double.doubleToLongBits
    CHAR:       2-byte representation of the character (reversed if small-endian)
    STRING_x:   a VINT_x indicating string length, then the string buffer directly
    STRING:     eq. STRING_3
  OBJECTS:
    ENUM <E extends java.lang.Enum>:        eq. STRING (enum constant name)
    COLL <T extends java.util.Collection>:  {
                                                INT length
                                                foreach entries {
                                                    MIXED entry-value
                                                }
                                            }
    ARRAY:                                  eq. COLL
    COMPOUND <T extends java.util.Map>:     {
                                                INT length
                                                foreach entries {
                                                    MIXED entry-key
                                                    MIXED entry-value
                                                }
                                            }
  CUSTOM:
    LOOP:       An enumeration of the types in the block, each entry led by a BOOLEAN TRUE, and finally terminated with a BOOLEAN FALSE

Version {
    BOOLEAN already-written whether this class already occurred in this stream
    IF already-written {
        SHORT   occur-order order of occurrence of this class in this stream starting from 0
    } ELSE {
        STRING  class-name
        SHORT   version-id
    }
}

SavedObject (null) {
    BOOLEAN FALSE constant
}

SavedObject {
    BOOLEAN TRUE constant
    LOOP class-versions all superclasses with @SavedObject until first already-written{
        Version version
    }
    LOOP class-properties all declared fields in class hierarchy with @SavedProperty annotation and @SavedProperty is not removed (removed=VERSION_NIL) {
        MIXED field-value of any type in the BASICS/OBJECTS legend, or a SavedObject
    }
}
```

## Limitations
Currently, this library does not support field declarations with two-dimensional type arguments, such as `List<List<String>>`, etc.
