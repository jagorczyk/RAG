"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { getChats } from "@/lib/api";

export default function Home() {
  const router = useRouter();

  useEffect(() => {
    getChats()
      .then((chats) => {
        if (chats.length > 0) {
          router.replace(`/chat/${chats[0].id}`);
          return;
        }
        router.replace("/folders");
      })
      .catch(() => {
        router.replace("/folders");
      });
  }, [router]);

  return null;
}
