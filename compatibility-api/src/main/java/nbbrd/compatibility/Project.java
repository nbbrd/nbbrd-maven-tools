package nbbrd.compatibility;

import lombok.NonNull;
import nbbrd.design.SealedType;

import java.net.URI;

@SealedType({Source.class, Target.class})
public interface Project {

    @NonNull
    URI getUri();

    @NonNull
    Filter getFilter();
}
