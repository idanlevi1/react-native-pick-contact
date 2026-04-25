import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

type Contact = {
  name: string;
  phone: string;
};

export interface Spec extends TurboModule {
  pickContact(): Promise<Contact | null>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('PickContact');
