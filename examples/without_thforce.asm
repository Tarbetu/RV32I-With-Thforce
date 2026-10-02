# Think about the following code as a function call with a return value.
# The function is called with the address of the function in rs1 and the argument in a0.
# The return value is stored in rd.
# The function is called with a sentinel check to see if it has been called before.
# If it has, the memorized result is returned.
# If not, the context is saved, the function is called, and the result is stored in rd.

# t5 holding the pointer of the result location (rd, which is {fn_ptr, value_ptr})
.set rd, t5
ADDI rd, 1000, 0
# t1 holding the pointer of the value (rs1)
.set rs1, t6
LA rs1, the_function

# Sentinel check
LW    t0, 0(rd)
BEQ   t0, rs1, forced

# Save the context
ADDI  sp, sp, -120
SW    x1,    0(sp)
SW    x3,    4(sp)
SW    x4,    8(sp)
SW    x5,   12(sp)
SW    x6,   16(sp)
SW    x7,   20(sp)
SW    x8,   24(sp)
SW    x9,   28(sp)
SW    x10,  32(sp)
SW    x11,  36(sp)
SW    x12,  40(sp)
SW    x13,  44(sp)
SW    x14,  48(sp)
SW    x15,  52(sp)
SW    x16,  56(sp)
SW    x17,  60(sp)
SW    x18,  64(sp)
SW    x19,  68(sp)
SW    x20,  72(sp)
SW    x21,  76(sp)
SW    x22,  80(sp)
SW    x23,  84(sp)
SW    x24,  88(sp)
SW    x25,  92(sp)
SW    x26,  96(sp)
SW    x27, 100(sp)
SW    x28, 104(sp)
SW    x29, 108(sp)
SW    x30, 112(sp)
SW    x31, 116(sp)

# Call the function, and prepare the first argument
ADDI  a0,   2, 0
JALR  ra, rs1, 0

# Write sentinel
return_point:
    SW    rs1, 0(rd)
    SW    a0,  4(rd)

    # Reload context from the snapshot
    LW    x1,   0(sp)
    LW    x3,   4(sp)
    LW    x4,   8(sp)
    LW    x5,  12(sp)
    LW    x6,  16(sp)
    LW    x7,  20(sp)
    LW    x8,  24(sp)
    LW    x9,  28(sp)
    LW    x10, 32(sp)
    LW    x11, 36(sp)
    LW    x12, 40(sp)
    LW    x13, 44(sp)
    LW    x14, 48(sp)
    LW    x15, 52(sp)
    LW    x16, 56(sp)
    LW    x17, 60(sp)
    LW    x18, 64(sp)
    LW    x19, 68(sp)
    LW    x20, 72(sp)
    LW    x21, 76(sp)
    LW    x22, 80(sp)
    LW    x23, 84(sp)
    LW    x24, 88(sp)
    LW    x25, 92(sp)
    LW    x26, 96(sp)
    LW    x27, 100(sp)
    LW    x28, 104(sp)
    LW    x29, 108(sp)
    LW    x30, 112(sp)
    LW    x31, 116(sp)
    ADDI  sp, sp, 120

forced:
    # Take the memorized result
    LW  a0, 4(rd)
    EBREAK

the_function:
    # This is the function that will be called.
    # It takes an argument in a0 and returns a value in a0.
    # For this example, we will just return the argument incremented by 2.
    ADDI a0, a0, 2
    RET
