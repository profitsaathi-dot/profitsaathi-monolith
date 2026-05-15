package com.profitsaathi.auth;

/**
 * Set as {@code Authentication#getPrincipal()} after JWT validation.
 * Inject via {@code @AuthenticationPrincipal AuthenticatedPrincipal me} in controllers.
 *
 * @param credentialsId  PK in {@code credentials} table (subject of the JWT)
 * @param subjectId      PK in either {@code sellers} or {@code customers}, depending on {@code role}
 * @param email          the credential email — convenient for audit and lookups
 * @param role           SELLER | CUSTOMER | ADMIN
 */
public record AuthenticatedPrincipal(
        Long credentialsId,
        Long subjectId,
        String email,
        String role
) {
    public boolean isSeller()   { return "SELLER".equals(role); }
    public boolean isCustomer() { return "CUSTOMER".equals(role); }
    public boolean isAdmin()    { return "ADMIN".equals(role); }
}
