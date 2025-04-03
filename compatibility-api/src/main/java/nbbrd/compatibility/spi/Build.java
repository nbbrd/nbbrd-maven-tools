package nbbrd.compatibility.spi;

import internal.compatibility.ResourceDefinition;

import java.io.Closeable;

@ResourceDefinition
public interface Build extends Closeable, Maven, Git {

}
