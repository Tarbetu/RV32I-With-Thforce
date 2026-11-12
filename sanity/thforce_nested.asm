LI sp, 4000
LI t5, 1000
SW x0, 0(t5)
LA t6, outer_function
LI a0, 5
.insn i 0b0001011, 0, t5, t6, 0
EBREAK

outer_function:
    LI t6, 0xABC
    LI t5, 2000
    SW x0, 0(t5)
    LA t4, nested_function
    .insn i 0b0001011, 0, t5, t4, 0
    ADDI s2, t6, 0
    ADDI s3, t4, 0
    RET

nested_function:
    LI t6, 0x456
    LI t5, 0x123
    LI t4, 0x789
    LI a0, 42
    RET
