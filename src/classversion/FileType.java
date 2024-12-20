package classversion;

enum FileType {
    JAR, CLASS;

    static FileType getByMagic(int magic) {
        if (magic == 0xCAFEBABE) {
            return CLASS;
        } else if (magic == 0x504B0304) {
            return JAR;
        } else {
            return null;
        }
    }
}
