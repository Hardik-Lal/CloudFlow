import { removeTestData } from "./support";

/** Leaves the stack's database as it was before the suite ran. */
export default function globalTeardown(): void {
  removeTestData();
}
