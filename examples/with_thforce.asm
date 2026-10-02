# I suggest to read the comments of `without_thforce.asm`
# This does the same thing

.set rd, t5
ADDI rd, 1000, 0
.set rs1, t6
LA rs1, the_function

ADDI a0, 2, 0
# The Assembly doesn't know anything about THFORCE yet :)
# The intent is: THFORCE rd, rs1, 1
# Where:
#  opcode is 0001011
#  rd is t5 with a name tag.
#  rs1 is t6 with a name tag.
#  immediate value is `1`, which is the id of the thunk slot.
.insn i 0001011, rd, 0, rs1, 1
EBREAK

the_function:
    # This is the function that will be called.
    # It takes an argument in a0 and returns a value in a0.
    # For this example, we will just return the argument incremented by 2.
    ADDI a0, a0, 2
    RET
