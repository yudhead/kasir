# Update Category Selection and Fixes

1. Update category selection in `TambahBarangActivity` to allow selecting existing categories or creating new ones.
2. Ensure consistent light theme for dialogs.

## Proposed Changes

### [Resources]

#### [activity_tambah_barang.xml](file:///C:/Users/Admin/AndroidStudioProjects/kasir/app/src/main/res/layout/activity_tambah_barang.xml)
- Change `etKategori` field to an `AutoCompleteTextView` to support both selection from a dropdown and manual entry.

### [Source Code]

#### [TambahBarangActivity.kt](file:///C:/Users/Admin/AndroidStudioProjects/kasir/app/src/main/java/com/kasir/TambahBarangActivity.kt)
- Fetch all existing products from Firebase to extract unique categories.
- Populate the `AutoCompleteTextView` with these categories.
- Maintain the ability for users to type a new category.

## Verification Plan

### Manual Verification
- **Dropdown List**: Open "Tambah Barang", tap the category field, and verify that existing categories (e.g., "Makanan", "Minuman") appear in the list.
- **New Category**: Type a new category name (e.g., "Elektronik") and save the product.
- **Verification**: Go to the Warehouse (Gudang) or Kasir and ensure the new product is correctly categorized.
- **Refresh**: Re-open "Tambah Barang" and verify the newly added category now appears in the dropdown list.
