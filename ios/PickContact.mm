#import <RNPickContactSpec/RNPickContactSpec.h>

#if __has_include("react_native_pick_contact-Swift.h")
#import "react_native_pick_contact-Swift.h"
#else
#import <react_native_pick_contact/react_native_pick_contact-Swift.h>
#endif

@interface PickContact : NSObject <NativePickContactSpec>
@property (nonatomic, strong) PickContactImpl *impl;
@end

@implementation PickContact

RCT_EXPORT_MODULE()

- (instancetype)init {
  self = [super init];
  if (self) {
    _impl = [[PickContactImpl alloc] init];
  }
  return self;
}

- (void)pickContact:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject {
  [_impl pickContactWithResolve:resolve reject:reject];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params {
  return std::make_shared<facebook::react::NativePickContactSpecJSI>(params);
}

@end
