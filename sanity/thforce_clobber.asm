LI sp, 4000
LI t5, 1000
SW x0, 0(t5)
LA t6, the_function
LI a0, 5
.insn i 0b0001011, 0, t5, t6, 0
EBREAK

the_function:
    LI t5, 0x123
    LI t6, 0x456
    ADDI a0, a0, 2
    RET
