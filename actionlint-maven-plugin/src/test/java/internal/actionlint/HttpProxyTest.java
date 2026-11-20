package internal.actionlint;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import org.junit.jupiter.api.Test;

class HttpProxyTest {

    @Test
    void bypassesNonProxyHostsAndNonNetworkUris() {
        HttpProxy proxy = HttpProxy.builder()
                .host("proxy.example.com")
                .port(8080)
                .nonProxyHost("*.internal")
                .nonProxyHost("LOCALHOST")
                .build();

        assertThat(proxy.toProxy(URI.create("https://github.com/x")))
                .isEqualTo(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.example.com", 8080)));
        assertThat(proxy.toProxy(URI.create("https://repo.internal/x"))).isEqualTo(Proxy.NO_PROXY);
        assertThat(proxy.toProxy(URI.create("http://localhost:8080/x"))).isEqualTo(Proxy.NO_PROXY);
        assertThat(proxy.toProxy(URI.create("file:/tmp/x"))).isEqualTo(Proxy.NO_PROXY);
    }

    @Test
    void matchesWildcardsLiterally() {
        assertThat(HttpProxy.matches("*.example.com", "api.example.com")).isTrue();
        assertThat(HttpProxy.matches("*.example.com", "example.com")).isFalse();
        assertThat(HttpProxy.matches("a.b", "aXb")).isFalse();
    }
}
