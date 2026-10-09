# Think about the following code as a function call with a return value.
# The function is called with the address of the function in rs1 and the argument in a0.
# The return value is stored in rd.
# The function is called with a sentinel check to see if it has been called before.
# If it has, the memorized result is returned.
# If not, the context is saved, the function is called, and the result is stored in rd.

# t5 holding the pointer of the result location (rd, which is {fn_ptr, value_ptr})
LI t5, 1000
# t1 holding the pointer of the value (rs1)
LA t6, the_function

# Sentinel check
LW    t0, 0(t5)
BEQ   t0, t6, forced

# Save the context
ADDI  sp, sp, -64
SW    ra,    0(sp)
SW    t0,    4(sp)
SW    t1,    8(sp)
SW    t2,   12(sp)
SW    t3,   16(sp)
SW    t4,   20(sp)
SW    t5,   24(sp)
SW    t6,   28(sp)
SW    a0,   32(sp)
SW    a1,   36(sp)
SW    a2,   40(sp)
SW    a3,   44(sp)
SW    a4,   48(sp)
SW    a5,   52(sp)
SW    a6,   56(sp)
SW    a7,   60(sp)

# Call the function, and prepare the first argument
LI    a0,  2
JALR  ra, t6, 0

# Write sentinel
return_point:
    SW    t6,  0(t5)
    SW    a0,  4(t5)

    # Reload context from the snapshot
    LW    ra,    0(sp)
    LW    t0,    4(sp)
    LW    t1,    8(sp)
    LW    t2,   12(sp)
    LW    t3,   16(sp)
    LW    t4,   20(sp)
    LW    t5,   24(sp)
    LW    t6,   28(sp)
    LW    a0,   32(sp)
    LW    a1,   36(sp)
    LW    a2,   40(sp)
    LW    a3,   44(sp)
    LW    a4,   48(sp)
    LW    a5,   52(sp)
    LW    a6,   56(sp)
    LW    a7,   60(sp)
    ADDI  sp, sp, 64

forced:
    # Take the memorized result
    LW  a0, 4(t5)
    EBREAK

the_function:
    # This is the function that will be called.
    # It takes an argument in a0 and returns a value in a0.
    # For this example, we will just return the argument incremented by 2.
    ADDI a0, a0, 2
    RET
