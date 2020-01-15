#include "vpi.h"

vpi_api_t* vpi_api = NULL;

extern "C" {

PLI_INT32 init_rsts_calltf(PLI_BYTE8 *user_data) {
  vpi_api->init_rsts();
  return 0;
}

PLI_INT32 init_ins_calltf(PLI_BYTE8 *user_data) {
  vpi_api->init_ins();
  return 0;
}

PLI_INT32 init_outs_calltf(PLI_BYTE8 *user_data) {
  vpi_api->init_outs();
  return 0;
}

PLI_INT32 init_sigs_calltf(PLI_BYTE8 *user_data) {
  vpi_api->init_sigs();
  vpi_api->init_channels();
  return 0;
}

PLI_INT32 tick_calltf(PLI_BYTE8 *user_data) {
  vpi_api->tick();
  return 0;
}

PLI_INT32 tick_compiletf(PLI_BYTE8 *user_data) {
  s_cb_data data_s;
  data_s.reason    = cbStartOfSimulation;
  data_s.cb_rtn    = sim_start_cb;
  data_s.obj       = NULL;
  data_s.time      = NULL;
  data_s.value     = NULL;
  data_s.user_data = NULL;
  vpi_free_object(vpi_register_cb(&data_s)); 
 
  data_s.reason    = cbEndOfSimulation;
  data_s.cb_rtn    = sim_end_cb;
  data_s.obj       = NULL;
  data_s.time      = NULL;
  data_s.value     = NULL;
  data_s.user_data = NULL;
  vpi_free_object(vpi_register_cb(&data_s));
  return 0;
}

PLI_INT32 sim_start_cb(p_cb_data cb_data) {
  vpi_api = new vpi_api_t;
  return 0;
}

PLI_INT32 sim_end_cb(p_cb_data cb_data) {
  delete vpi_api;
#ifndef __VSIM__
  // I guess this line should be removed for all backends since cbEndOfSimulation is triggered on vpi_control(vpiFinish, 0); (hence looping forever)
  vpi_control(vpiFinish, 0); 
# else 
  // double vpiFinish makes vsim segfault => it doesn't handle this infinite callback loop properly
  // vpi_control(vpiStop, 0); // this is harmless but useless ...
#endif
  return 0;
}

PLI_INT32 tick_cb(p_cb_data cb_data) {
  vpi_api->tick();
  return 0;
}

}
