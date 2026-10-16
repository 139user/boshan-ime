// 在 Linux 上用系统的 librime(+ lua 插件)实测博山方案:部署 build/rime,然后按给的输入打字、打印候选。
// 编译:cc -O1 -o build/rime_test tools/rime-test/rime_test.c -lrime
// 用法:build/rime_test <共享数据目录> <用户目录> [--deploy] [-o 选项=0|1]... <输入>...
//   每个 <输入> 单独一次:清空、逐个字母送进去、打印前 10 个候选(文字 + 注释)。
#include <rime_api.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(int argc, char** argv) {
  if (argc < 3) {
    fprintf(stderr, "用法:%s <共享目录> <用户目录> [--deploy] [-o 选项=0|1]... <输入>...\n", argv[0]);
    return 2;
  }
  RimeApi* api = rime_get_api();
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = argv[1];
  traits.user_data_dir = argv[2];
  traits.app_name = "rime.boshan-test";
  traits.distribution_name = "boshan-test";
  traits.distribution_code_name = "boshan-test";
  traits.distribution_version = "1";
  traits.log_dir = "";  // 不写日志文件
  traits.min_log_level = 2;
  api->setup(&traits);
  api->initialize(&traits);
  int deploy = 0;
  for (int i = 3; i < argc; i++)
    if (!strcmp(argv[i], "--deploy")) deploy = 1;
  if (deploy) {
    api->deploy();
    api->deploy_schema("boshan.schema.yaml");
  }
  api->start_maintenance(False);
  api->join_maintenance_thread();

  RimeSessionId s = api->create_session();
  api->select_schema(s, "boshan");
  for (int i = 3; i < argc; i++) {
    if (!strcmp(argv[i], "--deploy")) continue;
    if (!strcmp(argv[i], "-o") && i + 1 < argc) {
      char opt[128];
      strncpy(opt, argv[++i], sizeof opt - 1);
      opt[sizeof opt - 1] = 0;
      char* eq = strchr(opt, '=');
      if (eq) {
        *eq = 0;
        api->set_option(s, opt, atoi(eq + 1) ? True : False);
      }
      continue;
    }
    api->clear_composition(s);
    api->simulate_key_sequence(s, argv[i]);
    printf("== %s\n", argv[i]);
    RIME_STRUCT(RimeContext, ctx);
    if (api->get_context(s, &ctx)) {
      for (int k = 0; k < ctx.menu.num_candidates && k < 10; k++) {
        RimeCandidate* c = &ctx.menu.candidates[k];
        printf("  %d. %s%s%s\n", k + 1, c->text, c->comment ? "  " : "", c->comment ? c->comment : "");
      }
      api->free_context(&ctx);
    }
    RIME_STRUCT(RimeCommit, commit);
    if (api->get_commit(s, &commit)) {
      printf("  上屏:%s\n", commit.text);
      api->free_commit(&commit);
    }
  }
  api->destroy_session(s);
  api->finalize();
  return 0;
}
