.source "T_shl_int_2addr_6.java"
.class  public Ldot/junit/opcodes/shl_int_2addr/d/T_shl_int_2addr_6;
.super  Ljava/lang/Object;


.method public constructor <init>()V
.registers 1

       invoke-direct {v0}, Ljava/lang/Object;-><init>()V
       return-void
.end method

.method public run(FF)I
.registers 8

       shl-int/2addr v6, v7
       return v6
.end method
