package distributed.message;

public enum ContentCode {
    // Operations
    ADD,    // Addition operation
    SUB,    // Subtraction operation
    MUL,    // Multiplication operation
    DIV,    // Division operation
    // Acknowledgement
    ACK_ADD,
    ACK_SUB,
    ACK_MUL,
    ACK_DIV,
    // Result
    RES,
    // Service Injection
    INY,
}
