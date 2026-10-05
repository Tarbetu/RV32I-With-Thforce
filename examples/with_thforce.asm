# I suggest to read the comments of `without_thforce.asm`
# This does the same thing

LI t5, 1000
LA t6, the_function

# The Assembly doesn't know anything about THFORCE yet :)
# The intent is: THFORCE rd, rs1, 1
# Where:
#  opcode is 0001011
#  rd is the value of t5
#  rs1 is the value of t6
#  immediate value is `1`, which is the id of the thunk slot.
.insn i 0b0001011, 0, t5, t6, 1
EBREAK

the_function:
    # This is the function that will be called.
    # It takes an argument in a0 and returns a value in a0.
    # For this example, we will just return the argument incremented by 2.
    ADDI a0, a0, 2
    RET
