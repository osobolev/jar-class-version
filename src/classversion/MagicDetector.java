package classversion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;

final class MagicDetector {

    final InputStream fullStream;
    final InputStream restStream;
    final FileType fileType;

    private MagicDetector(InputStream fullStream, InputStream restStream, FileType fileType) {
        this.fullStream = fullStream;
        this.restStream = restStream;
        this.fileType = fileType;
    }

    static MagicDetector detect(InputStream is) throws IOException {
        byte[] buf = new byte[4];
        int read = is.readNBytes(buf, 0, buf.length);
        ByteArrayInputStream stream1 = new ByteArrayInputStream(buf, 0, read);
        FileType fileType;
        if (read < buf.length) {
            fileType = null;
        } else {
            int magic = ByteBuffer.wrap(buf).getInt();
            fileType = FileType.getByMagic(magic);
        }
        return new MagicDetector(new SequenceInputStream(stream1, is), is, fileType);
    }
}
