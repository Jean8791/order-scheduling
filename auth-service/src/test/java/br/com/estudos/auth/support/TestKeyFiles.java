package br.com.estudos.auth.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public record TestKeyFiles(Path privateKey, Path publicKey) {

    public static TestKeyFiles generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new TestKeyFiles(
                    write("jwt-private", pem("PRIVATE KEY", pair.getPrivate().getEncoded())),
                    write("jwt-public", pem("PUBLIC KEY", pair.getPublic().getEncoded())));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String privateKeyLocation() {
        return "file:" + privateKey.toAbsolutePath();
    }

    public String publicKeyLocation() {
        return "file:" + publicKey.toAbsolutePath();
    }

    private static Path write(String prefix, String content) {
        try {
            Path file = Files.createTempFile(prefix, ".pem");
            file.toFile().deleteOnExit();
            Files.writeString(file, content);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
