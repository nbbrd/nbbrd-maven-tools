package internal.actionlint;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

/** An HTTP proxy, as configured in Maven settings. */
@Value
@lombok.Builder
public class HttpProxy {

    @NonNull String host;

    int port;

    /** Host patterns that bypass the proxy; {@code *} matches any characters. */
    @Singular
    List<String> nonProxyHosts;

    public @NonNull Proxy toProxy(@NonNull URI uri) {
        String target = uri.getHost();
        if (target == null || nonProxyHosts.stream().anyMatch(pattern -> matches(pattern, target))) {
            return Proxy.NO_PROXY;
        }
        return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port));
    }

    static boolean matches(String pattern, String host) {
        String regex = Pattern.quote(pattern.trim().toLowerCase(Locale.ROOT)).replace("*", "\\E.*\\Q");
        return host.toLowerCase(Locale.ROOT).matches(regex);
    }
}
