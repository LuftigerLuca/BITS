import * as React from "react"
import { Combobox as ComboboxPrimitive } from "@base-ui/react/combobox"
import { cn } from "cn"
import { CheckIcon, ChevronDownIcon, SearchIcon } from "lucide-react"

export interface SearchableSelectItem {
  value: string
  /** Shown in the list and on the trigger; the search looks only at this. */
  label: string
  /** Never filtered out, e.g. "Alle" in a filter. */
  pinned?: boolean
}

/** Without accents and case, so "osterr" finds "Österreich". */
function normalize(text: string) {
  return text.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLocaleLowerCase("de")
}

/** Whether [item] stays in the list for [query]: pinned, or the query anywhere in the label. */
export function matchesSearch(item: SearchableSelectItem, query: string) {
  return item.pinned === true || normalize(item.label).includes(normalize(query.trim()))
}

/**
 * Looks like `Select`, but the open list starts with a search field that has the focus; for long lists of data from
 * Discord or WarEra. Items keep their order; the search text is cleared on closing.
 */
function SearchableSelect({
  id,
  items,
  value,
  onValueChange,
  placeholder,
  disabled,
  className,
  renderItem,
  "aria-describedby": ariaDescribedBy,
}: {
  /** For the `Label` of the field; goes to the trigger. */
  id?: string
  items: SearchableSelectItem[]
  value: string | null
  onValueChange: (value: string | null) => void
  placeholder?: React.ReactNode
  disabled?: boolean
  className?: string
  /** How an item looks in the list, e.g. a role in its colour; its label by default. */
  renderItem?: (item: SearchableSelectItem) => React.ReactNode
  "aria-describedby"?: string
}) {
  const [query, setQuery] = React.useState("")
  const byValue = React.useMemo(() => new Map(items.map((item) => [item.value, item])), [items])
  const values = React.useMemo(() => items.map((item) => item.value), [items])

  return (
    <ComboboxPrimitive.Root
      items={values}
      value={value}
      onValueChange={(next) => onValueChange(next)}
      inputValue={query}
      onInputValueChange={setQuery}
      onOpenChange={(open) => {
        if (!open) setQuery("")
      }}
      itemToStringLabel={(itemValue: string) => byValue.get(itemValue)?.label ?? ""}
      filter={(itemValue: string, search: string) => {
        const item = byValue.get(itemValue)
        return item !== undefined && matchesSearch(item, search)
      }}
      disabled={disabled}
    >
      <ComboboxPrimitive.Trigger
        id={id}
        data-slot="searchable-select-trigger"
        aria-describedby={ariaDescribedBy}
        className={cn(
          "flex h-8 w-fit items-center justify-between gap-1.5 rounded-lg border border-input bg-transparent py-2 pr-2 pl-2.5 text-sm whitespace-nowrap transition-colors outline-none select-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 data-placeholder:text-muted-foreground dark:bg-input/30 dark:hover:bg-input/50 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
          className
        )}
      >
        <span data-slot="searchable-select-value" className="line-clamp-1 flex flex-1 text-left">
          <ComboboxPrimitive.Value placeholder={placeholder}>
            {(selected: string | null) => (selected === null ? placeholder : byValue.get(selected)?.label)}
          </ComboboxPrimitive.Value>
        </span>
        <ChevronDownIcon className="pointer-events-none size-4 text-muted-foreground" />
      </ComboboxPrimitive.Trigger>
      <ComboboxPrimitive.Portal>
        <ComboboxPrimitive.Positioner sideOffset={4} className="isolate z-50">
          <ComboboxPrimitive.Popup
            data-slot="searchable-select-content"
            className="relative isolate z-50 flex max-h-[min(var(--available-height),24rem)] w-(--anchor-width) min-w-36 origin-(--transform-origin) flex-col overflow-hidden rounded-lg bg-popover text-popover-foreground shadow-md ring-1 ring-foreground/10 duration-100 data-open:animate-in data-open:fade-in-0 data-open:zoom-in-95 data-closed:animate-out data-closed:fade-out-0 data-closed:zoom-out-95"
          >
            <div className="flex items-center gap-1.5 border-b px-2.5">
              <SearchIcon className="size-4 shrink-0 text-muted-foreground" />
              <ComboboxPrimitive.Input
                placeholder="Suchen …"
                aria-label="Suchen"
                className="h-8 w-full min-w-0 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              />
            </div>
            <ComboboxPrimitive.Empty className="px-2.5 py-2 text-sm text-muted-foreground empty:hidden">
              Keine Treffer
            </ComboboxPrimitive.Empty>
            <ComboboxPrimitive.List className="overflow-y-auto p-1 empty:hidden">
              {(itemValue: string) => {
                const item = byValue.get(itemValue)
                if (!item) return null
                return (
                  <ComboboxPrimitive.Item
                    key={item.value}
                    value={item.value}
                    className="relative flex w-full cursor-default items-center gap-1.5 rounded-md py-1 pr-8 pl-1.5 text-sm outline-hidden select-none data-highlighted:bg-accent data-highlighted:text-accent-foreground data-disabled:pointer-events-none data-disabled:opacity-50"
                  >
                    <span className="flex flex-1 shrink-0 gap-2 whitespace-nowrap">
                      {renderItem ? renderItem(item) : item.label}
                    </span>
                    <ComboboxPrimitive.ItemIndicator className="pointer-events-none absolute right-2 flex size-4 items-center justify-center">
                      <CheckIcon className="pointer-events-none size-4" />
                    </ComboboxPrimitive.ItemIndicator>
                  </ComboboxPrimitive.Item>
                )
              }}
            </ComboboxPrimitive.List>
          </ComboboxPrimitive.Popup>
        </ComboboxPrimitive.Positioner>
      </ComboboxPrimitive.Portal>
    </ComboboxPrimitive.Root>
  )
}

export { SearchableSelect }
