// See LICENSE for license details.

package examples

import chisel3._
import chisel3.util.{DeqIO, EnqIO, log2Ceil}
import chisel3.iotesters.{ChiselFlatSpec, OrderedDecoupledHWIOTester}

object Router {
  val addressWidth    = 32
  val dataWidth       = 64
  val headerWidth     =  8
  val routeTableSize  = 15
  val numberOfOutputs =  4
}

class ReadCmd extends Bundle {
  val addr = UInt(Router.addressWidth.W)
}

class WriteCmd extends ReadCmd {
  val data = UInt(Router.addressWidth.W)
}

class Packet extends Bundle {
  val header = UInt(Router.headerWidth.W)
  val body   = UInt(Router.dataWidth.W)
}


/**
  * This router circuit
  * It routes a packet placed on it's input to one of n output ports
  *
  * @param n is number of fanned outputs for the routed packet
  */
class RouterIO(n: Int) extends Bundle {
//  override def cloneType           = new RouterIO(n).asInstanceOf[this.type]
  val read_routing_table_request   = DeqIO(new ReadCmd())
  val read_routing_table_response  = EnqIO(UInt(Router.addressWidth.W))
  val load_routing_table_request   = DeqIO(new WriteCmd())
  val in                           = DeqIO(new Packet())
  val outs                         = Vec(n, EnqIO(new Packet()))
}

/**
  * routes packets by using their header as an index into an externally loaded and readable table,
  * The number of addresses recognized does not need to match the number of outputs
  */
class Router extends Module {
  val depth = Router.routeTableSize
  val n     = Router.numberOfOutputs
  val io    = IO(new RouterIO(n))
  val tbl   = Mem(depth, UInt(BigInt(n).bitLength.W))

  io.read_routing_table_request.nodeq()
  io.load_routing_table_request.nodeq()
  io.read_routing_table_response.noenq()
  io.read_routing_table_response.bits := 0.U
  io.in.nodeq()
  io.outs.foreach { out =>
    out.bits := 0.U.asTypeOf(out.bits)
    out.noenq()
  }

  when(io.read_routing_table_request.valid && io.read_routing_table_response.ready) {
    io.read_routing_table_response.enq(tbl(
      io.read_routing_table_request.deq().addr
    ))
  }
  .elsewhen(io.load_routing_table_request.valid) {
    val cmd = io.load_routing_table_request.deq()
    tbl(cmd.addr) := cmd.data
    printf("setting tbl(%d) to %d\n", cmd.addr, cmd.data)
  }
  .elsewhen(io.in.valid) {
    val pkt = io.in.bits
    val idx = tbl(pkt.header(log2Ceil(Router.routeTableSize), 0))
    when(io.outs(idx).ready) {
      io.in.deq()
      io.outs(idx).enq(pkt)
      printf("got packet to route header %d, data %d, being routed to out(%d)\n", pkt.header, pkt.body, tbl(pkt.header))
    }
  }
}

class RouterUnitTester(number_of_packets_to_send: Int) extends OrderedDecoupledHWIOTester {
  val device_under_test = Module(new Router)
  val c = device_under_test
  enable_all_debug = true

  rnd.setSeed(0)

  def readRoutingTable(addr: Int, data: Int): Unit = {
    inputEvent(c.io.read_routing_table_request.bits.addr -> addr)
    outputEvent(c.io.read_routing_table_response.bits -> data)
  }

  def writeRoutingTable(addr: Int, data: Int): Unit = {
    inputEvent(
      c.io.load_routing_table_request.bits.addr -> addr,
      c.io.load_routing_table_request.bits.data -> data
    )
  }

  def writeRoutingTableWithConfirm(addr: Int, data: Int): Unit = {
    writeRoutingTable(addr, data)
    readRoutingTable(addr, data)
  }

  def routePacket(header: Int, body: Int, routed_to: Int): Unit = {
    inputEvent(c.io.in.bits.header -> header, c.io.in.bits.body -> body)
    outputEvent(c.io.outs(routed_to).bits.body -> body)
    logScalaDebug(s"rout_packet $header $body should go to out($routed_to)")
  }

  readRoutingTable(0, 0) // confirm we initialized the routing table

  // load routing table, confirm each write as built
  for (i <- 0 until Router.numberOfOutputs) {
    writeRoutingTableWithConfirm(i, (i + 1) % Router.numberOfOutputs)
  }
  // check them in reverse order just for fun
  for (i <- Router.numberOfOutputs - 1 to 0 by -1) {
    readRoutingTable(i, (i + 1) % Router.numberOfOutputs)
  }

  // send some regular packets
  for (i <- 0 until Router.numberOfOutputs) {
    routePacket(i, i * 3, (i + 1) % 4)
  }

  // generate a new routing table
  val new_routing_table = Array.tabulate(Router.routeTableSize) { _ =>
    scala.util.Random.nextInt(Router.numberOfOutputs)
  }

  // load a new routing table
  for ((destination, index) <- new_routing_table.zipWithIndex) {
    writeRoutingTable(index, destination)
  }

  // send a bunch of packets, with random values
  for (i <- 0 until number_of_packets_to_send) {
    val data = rnd.nextInt(Int.MaxValue - 1)
    routePacket(i % Router.routeTableSize, data, new_routing_table(i % Router.routeTableSize))
  }
}

class RouterUnitTesterSpec extends ChiselFlatSpec {
  val number_of_packets = 20
  "a router" should "can have it's rout table loaded and changed and route a bunch of packets" in {
    assertTesterPasses {
      new RouterUnitTester(number_of_packets)
    }
  }
}
