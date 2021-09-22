/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef RUNTIME_GC_COMMON_GC_SCHEDULER_H
#define RUNTIME_GC_COMMON_GC_SCHEDULER_H

#include <atomic>
#include <cinttypes>
#include <cstddef>
#include <functional>

#include "Types.h"
#include "Utils.hpp"

namespace kotlin {
namespace gc {

namespace internal {

// TODO: May be specialize standard things
// TODO: Or move these to some other place?
class VectorComparator {;
public:
    bool operator()(const KStdVector<void*>& lhs, const KStdVector<void*>& rhs) const noexcept {
        if (lhs.size() != rhs.size()) {
            return false;
        }

        for(size_t i = 0; i < lhs.size(); i++) {
            if (lhs[i] != rhs[i]) {
                return false;
            }
        }

        return true;
    }
};

class VectorHasher {
public:
    VectorHasher() = default;
    VectorHasher(VectorHasher&&) = default;
    VectorHasher(const VectorHasher&) = default;

    size_t operator()(const KStdVector<void*>& vector) const noexcept {
        size_t result = 0;
        std::hash<void*> hash;
        for (auto v : vector) {
            result = result * 31 + hash(v);
        }
        return result;
    }
};

} // namespace internal

struct GCSchedulerConfig {
    std::atomic<size_t> threshold = 100000; // Roughly 1 safepoint per 10ms (on a subset of examples on one particular machine).
    std::atomic<size_t> allocationThresholdBytes = 10 * 1024 * 1024; // 10MiB by default.
    std::atomic<uint64_t> cooldownThresholdNs = 200 * 1000 * 1000; // 200 milliseconds by default.
    std::atomic<bool> autoTune = false;

    GCSchedulerConfig() noexcept;
};

// TODO: Consider calling GC from the scheduler itself.
class GCScheduler : private Pinned {
public:
    class ThreadData {
    public:
        using OnSafePointCallback = std::function<bool(size_t, size_t)>;

        static constexpr size_t kFunctionEpilogueWeight = 1;
        static constexpr size_t kLoopBodyWeight = 1;
        static constexpr size_t kExceptionUnwindWeight = 1;

        explicit ThreadData(GCSchedulerConfig& config, OnSafePointCallback onSafePoint) noexcept :
            config_(config), onSafePoint_(std::move(onSafePoint)) {
            ClearCountersAndUpdateThresholds();
        }

        // Should be called on encountering a safepoint.
        bool OnSafePointRegular(size_t weight) noexcept {
            safePointsCounter_ += weight;
            if (safePointsCounter_ < safePointsCounterThreshold_) {
                return false;
            }
            return OnSafePointSlowPath();
        }

        // Should be called on encountering a safepoint placed by the allocator.
        // TODO: Should this even be a safepoint (i.e. a place, where we suspend)?
        bool OnSafePointAllocation(size_t size) noexcept {
            allocatedBytes_ += size;
            if (allocatedBytes_ < allocatedBytesThreshold_) {
                return false;
            }
            return OnSafePointSlowPath();
        }

        void OnStoppedForGC() noexcept { ClearCountersAndUpdateThresholds(); }

    private:
        bool OnSafePointSlowPath() noexcept;
        void ClearCountersAndUpdateThresholds() noexcept;

        GCSchedulerConfig& config_;
        OnSafePointCallback onSafePoint_;

        size_t allocatedBytes_ = 0;
        size_t allocatedBytesThreshold_ = 0;
        size_t safePointsCounter_ = 0;
        size_t safePointsCounterThreshold_ = 0;
    };

    class GCData {
    public:
        using CurrentTimeCallback = std::function<uint64_t()>;

        GCData(GCSchedulerConfig& config, CurrentTimeCallback currentTimeCallbackNs) noexcept;

        // May be called by different threads via `ThreadData`.
        bool OnSafePoint(size_t allocatedBytes, size_t safePointsCounter) noexcept;

        NO_EXTERNAL_CALLS_CHECK bool OnSafePointAggressive(size_t allocatedBytes, size_t safePointsCounter) noexcept;

        // Always called by the GC thread.
        void OnPerformFullGC() noexcept;

    private:
        using SafepointID = KStdVector<void*>;

        GCSchedulerConfig& config_;
        CurrentTimeCallback currentTimeCallbackNs_;

        std::atomic<uint64_t> timeOfLastGcNs_;

        // TODO: Needed for the aggressive mode only. May be create it lazily?
        std::unordered_set<SafepointID, internal::VectorHasher, internal::VectorComparator, KonanAllocator<SafepointID>> metSafePoints_;
    };

    GCScheduler() noexcept;

    GCSchedulerConfig& config() noexcept { return config_; }
    GCData& gcData() noexcept { return gcData_; }
    ThreadData NewThreadData() noexcept;

private:
    GCSchedulerConfig config_;
    GCData gcData_;
};

} // namespace gc
} // namespace kotlin

#endif // RUNTIME_GC_COMMON_GC_SCHEDULER_H
