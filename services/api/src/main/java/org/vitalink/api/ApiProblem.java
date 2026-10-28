package org.vitalink.api;

public class ApiProblem extends RuntimeException {
    private final int status;
    private final String code;
    public ApiProblem(int status, String code, String detail) {
        super(detail); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static ApiProblem badInput(String detail) { return new ApiProblem(400,"INVALID_INPUT",detail); }
    public static ApiProblem notFound() { return new ApiProblem(404,"NOT_FOUND","Referral not found."); }
    public static ApiProblem forbidden() { return new ApiProblem(403,"FORBIDDEN","This action is not authorized."); }
    public static ApiProblem unauthorized() { return new ApiProblem(401,"UNAUTHENTICATED","Sign in with valid credentials."); }
    public static ApiProblem conflict() { return new ApiProblem(409,"CONFLICT","The immutable snapshot or state conflicts with this request."); }
}
