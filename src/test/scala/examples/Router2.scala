// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util._
import chisel3.iotesters._

object Router2 {
  val addressWidth    = 32
  val dataWidth       = 64
  val headerWidth     =  8
  val routeTableSize  =  4
  val numberOfOutputs =  4
}

class ReadCmd2 extends Bundle {
  val addr = UInt(width = Router2.addressWidth)
}

class WriteCmd2 extends ReadCmd2 {
  val data = UInt(width = Router2.addressWidth)
}

class Packet2 extends Bundle {
  val header = UInt(width = Router2.headerWidth)
  val body   = Bits(width = Router2.dataWidth)
}

/**
  * This router circuit
  * It routes a packet placed on it's input to one of n output ports
  *
  * @param n is number of fanned outputs for the routed packet
  */
class Router2IO(n: Int) extends Bundle {
  //  override def cloneType           = new Router2IO(n).asInstanceOf[this.type]
  val read_routing_table_request   = new DeqIO(new ReadCmd2())
  val read_routing_table_response  = new EnqIO(UInt(width = Router2.addressWidth))
  val load_routing_table_request   = new DeqIO(new WriteCmd2())
  val in                           = new DeqIO(new Packet2())
  val outs                         = Vec(n, new EnqIO(new Packet2()))
}

/**
  * routes packets by using their header as an index into an externally loaded and readable table,
  * The number of addresses recognized does not need to match the number of outputs
  */
class Router2 extends Module {
  val depth = Router2.routeTableSize
  val n     = Router2.numberOfOutputs
  val io    = new Router2IO(n)
  val tbl   = Mem(depth, UInt(width = BigInt(n).bitLength))

  when(reset) {
    tbl.indices.foreach { index =>
      tbl(index) := UInt(0, width = Router2.addressWidth)
    }
  }

  io.read_routing_table_request.init()
  io.load_routing_table_request.init()
  io.read_routing_table_response.init()
  io.in.init()
  io.outs.foreach { out => out.init() }

  when(io.read_routing_table_request.valid && io.read_routing_table_response.ready) {
    io.read_routing_table_response.enq(tbl(
      {
        val addr = io.read_routing_table_request.deq().addr
        // printf("device: tbl(addr=%d) routes to %d\n", addr, tbl(addr))
        addr
      }
    ))
  }
    .elsewhen(io.load_routing_table_request.valid) {
      val cmd = io.load_routing_table_request.deq()
      tbl(cmd.addr) := cmd.data
      // printf("device: setting tbl(%d) to %d\n", cmd.addr, cmd.data)
    }
    .elsewhen(io.in.valid) {
      val pkt = io.in.bits
      val idx = tbl(pkt.header(log2Up(Router2.routeTableSize), 0))
      when(io.outs(idx).ready) {
        io.in.deq()
        io.outs(idx).enq(pkt)
        // printf("device: packet(header=%d, data=%d), being routed to out(%d)\n", pkt.header, pkt.body, tbl(pkt.header))
      }
    }
}

class Router2UnitTester(number_of_packets_to_send: Int, c: Router2, backend: Option[Backend] = None) extends PeekPokeTester(c, _backend = backend) {
  rnd.setSeed(0)

  def readRoutingTable(addr: Int, data: Int): Unit = {
    poke(c.io.read_routing_table_request.bits.addr, addr)
    poke(c.io.read_routing_table_request.valid, 1)
    poke(c.io.read_routing_table_response.ready, 1)

    while(peek(c.io.read_routing_table_response.valid) == BigInt(0)) { step(1) }

    println(s"tester: read table(addr=$addr) is ${peek(c.io.read_routing_table_response.bits)} expected $data")
    expect(c.io.read_routing_table_response.bits, data)
    step(1)

    poke(c.io.read_routing_table_request.valid, 0)
    poke(c.io.read_routing_table_response.ready, 1)

  }

  def writeRoutingTable(addr: Int, data: Int): Unit = {
//    while(peek(c.io.load_routing_table_request.valid) == BigInt(0)) { step(1)}
    poke(c.io.load_routing_table_request.bits.addr, addr)
    poke(c.io.load_routing_table_request.bits.data, data)
    poke(c.io.load_routing_table_request.valid, 1)
    println(s"tester: writing table(addr=$addr) <= $data done")
    step(1)
    poke(c.io.load_routing_table_request.valid, 0)
  }

  def writeRoutingTableWithConfirm(addr: Int, data: Int): Unit = {
    writeRoutingTable(addr, data)
    readRoutingTable(addr, data)
  }

  def routePacket2(header: Int, body: Int, routed_to: Int): Unit = {
//    while(peek(c.io.in.valid) == BigInt(0)) { step(1) }
    poke(c.io.in.bits.header, header)
    poke(c.io.in.bits.body, body)
    poke(c.io.in.valid, 1)
    poke(c.io.outs(routed_to).ready, 1)

    var count = 0
    while(peek(c.io.outs(routed_to).valid) == BigInt(0)) {
      count += 1
      if(count > 5) {
        System.exit(0)
      }
      step(1)
    }
    poke(c.io.outs(routed_to).bits.body, body)
    println(s"rout_packet $header $body should go to out($routed_to)")
  }

  readRoutingTable(0, 0) // confirm we initialized the routing table

  // load routing table, confirm each write as built
  for (i <- 0 until Router2.numberOfOutputs) {
    writeRoutingTableWithConfirm(i, (i + 1) % Router2.numberOfOutputs)
  }
  // check them in reverse order just for fun
  for (i <- Router2.numberOfOutputs - 1 to 0 by -1) {
    readRoutingTable(i, (i + 1) % Router2.numberOfOutputs)
  }

  // send some regular packets
  for (i <- 0 until Router2.numberOfOutputs) {
    routePacket2(i, i * 3, (i + 1) % 4)
  }

  // generate a new routing table
  val new_routing_table = Array.tabulate(Router2.routeTableSize) { _ =>
    scala.util.Random.nextInt(Router2.numberOfOutputs)
  }

  // load a new routing table
  for ((destination, index) <- new_routing_table.zipWithIndex) {
    writeRoutingTable(index, destination)
  }

  // send a bunch of packets, with random values
  for (i <- 0 until number_of_packets_to_send) {
    val data = rnd.nextInt(Int.MaxValue - 1)
    routePacket2(i % Router2.routeTableSize, data, new_routing_table(i % Router2.routeTableSize))
  }
}

class Router2UnitTesterSpec extends ChiselFlatSpec {
  val number_of_packets = 20
  "a router" should "can have it's rout table loaded and changed and route a bunch of packets" in {
    runPeekPokeTester(() => new Router2, "firrtl"){
      (c,b) => new Router2UnitTester(20, c,b)} should be (true)
  }
}
