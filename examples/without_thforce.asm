# Sentinel check
LW    t0, 0(rd)
BEQ   t0, rs1, forced

# Save the context
ADDI  sp, sp, -124
SW    x1,   0(sp)
SW    x2,   4(sp)
SW    x3,   8(sp)
SW    x4,  12(sp)
SW    x5,  16(sp)
SW    x6,  20(sp)
SW    x7,  24(sp)
SW    x8,  28(sp)
SW    x9,  32(sp)
SW    x10, 36(sp)
SW    x11, 40(sp)
SW    x12, 44(sp)
SW    x13, 48(sp)
SW    x14, 52(sp)
SW    x15, 56(sp)
SW    x16, 60(sp)
SW    x17, 64(sp)
SW    x18, 68(sp)
SW    x19, 72(sp)
SW    x20, 76(sp)
SW    x21, 80(sp)
SW    x22, 84(sp)
SW    x23, 88(sp)
SW    x24, 92(sp)
SW    x25, 96(sp)
SW    x26, 100(sp)
SW    x27, 104(sp)
SW    x28, 108(sp)
SW    x29, 112(sp)
SW    x30, 116(sp)
SW    x31, 120(sp)

# Call the function
ADDI  ra, pc, 8
JALR  x1, rs1, 0

# Write sentinel
SW    rs1, 0(rd)
SW    a0,  4(rd)

# Reload context from the snapshot
LW    x1,   0(sp)
LW    x2,   4(sp)
LW    x3,   8(sp)
LW    x4,  12(sp)
LW    x5,  16(sp)
LW    x6,  20(sp)
LW    x7,  24(sp)
LW    x8,  28(sp)
LW    x9,  32(sp)
LW    x10, 36(sp)
LW    x11, 40(sp)
LW    x12, 44(sp)
LW    x13, 48(sp)
LW    x14, 52(sp)
LW    x15, 56(sp)
LW    x16, 60(sp)
LW    x17, 64(sp)
LW    x18, 68(sp)
LW    x19, 72(sp)
LW    x20, 76(sp)
LW    x21, 80(sp)
LW    x22, 84(sp)
LW    x23, 88(sp)
LW    x24, 92(sp)
LW    x25, 96(sp)
LW    x26, 100(sp)
LW    x27, 104(sp)
LW    x28, 108(sp)
LW    x29, 112(sp)
LW    x30, 116(sp)
LW    x31, 120(sp)
ADDI  sp, sp, 124

forced:
    # Take the memorized result
    LW  rd, 4(rd)
