import NativePickContact from './NativePickContact';

export type Contact = {
  name: string;
  phone: string;
};

export function pickContact(): Promise<Contact | null> {
  return NativePickContact.pickContact();
}
