#include <cassert>
#include "../../main/cpp/prompt_budget.h"

int main() {
    assert(prompt_fits(512, 512, 2048, 256));
    assert(!prompt_fits(513, 512, 2048, 256));
    assert(!prompt_fits(300, 512, 512, 256));
    assert(!prompt_fits(1, 512, 256, 256));
    assert(!prompt_fits(100, 64, 2048, 256));
}
