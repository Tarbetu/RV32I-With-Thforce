SECTIONS {
. = 0x0;
.text : { *(.text*) }
/DISCARD/ : { *(.riscv.attributes) *(.comment) *(.note*) }
}
