LA t0, target
ADDI t0, t0, -3
JALR x0, t0, 3
LI a0, 0xBAD
EBREAK

target:
    LI a0, 0x123
done:
    EBREAK
