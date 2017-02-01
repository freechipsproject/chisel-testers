module BBAddThree(
    input  [15:0] in,
    output reg [15:0] out
);
  always @* begin
  out <= in + 3;
  end
endmodule
